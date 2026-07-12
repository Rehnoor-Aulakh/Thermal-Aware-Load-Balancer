package org.example;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * GPU stress engine using OpenCL tensor (matrix) multiplications.
 *
 * This engine discovers the first available GPU via OpenCL,
 * compiles a matrix multiplication kernel, and provides a
 * method to generate GPU load for a given duration and
 * intensity level.
 *
 * If no OpenCL-capable GPU is found, {@link #isAvailable()}
 * returns {@code false} and all load methods become no-ops.
 *
 * The GPU load intensity is controlled by mapping the
 * target percentage (0–100) to:
 *
 *   - Matrix size (larger = more GPU work per multiplication)
 *   - Sleep gaps between multiplications (shorter = higher load)
 *
 * This mirrors how the CPU stress uses duty-cycle spinning
 * but adapted for GPU compute characteristics.
 */
public final class GpuStressEngine {


    // ============================================================
    // MATRIX MULTIPLICATION KERNEL (OpenCL C)
    // ============================================================

    private static final String KERNEL_SOURCE = """
            __kernel void matMul(
                    __global const float* A,
                    __global const float* B,
                    __global float* C,
                    const int N
            ) {
                int row = get_global_id(1);
                int col = get_global_id(0);

                float sum = 0.0f;

                for (int k = 0; k < N; k++) {
                    sum += A[row * N + k] * B[k * N + col];
                }

                C[row * N + col] = sum;
            }
            """;


    // ============================================================
    // LOAD SCALING CONFIGURATION
    // ============================================================

    /**
     * Matrix sizes mapped to load ranges.
     *
     *   0%       -> idle (no GPU work)
     *  10-30%    -> 256x256
     *  40-50%    -> 512x512
     *  60-70%    -> 768x768
     *  80-100%   -> 1024x1024
     */
    private static final int SIZE_SMALL  = 256;
    private static final int SIZE_MEDIUM = 512;
    private static final int SIZE_LARGE  = 768;
    private static final int SIZE_MAX    = 1024;

    /**
     * Sleep between GPU kernel executions (in milliseconds)
     * mapped to load intensity.
     *
     * Higher load -> shorter sleep -> more GPU utilisation.
     */
    private static final long SLEEP_LOW_MS    = 50;
    private static final long SLEEP_MEDIUM_MS = 20;
    private static final long SLEEP_HIGH_MS   = 5;
    private static final long SLEEP_MAX_MS    = 0;


    // ============================================================
    // OPENCL HANDLES
    // ============================================================

    private final boolean available;

    private cl_context context;
    private cl_command_queue commandQueue;
    private cl_program program;
    private cl_kernel kernel;
    private cl_device_id device;

    private String deviceName = "none";


    // ============================================================
    // PRE-ALLOCATED BUFFERS (for the largest matrix size)
    // ============================================================

    private cl_mem bufferA;
    private cl_mem bufferB;
    private cl_mem bufferC;

    private float[] hostA;
    private float[] hostB;
    private float[] hostC;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    /**
     * Attempts to initialise OpenCL and discover a GPU.
     *
     * If initialisation fails for any reason, the engine
     * marks itself as unavailable and all methods become
     * safe no-ops.
     */
    public GpuStressEngine() {

        boolean success = false;

        try {

            success = initialiseOpenCL();

        } catch (Exception error) {

            System.err.println(
                    "GPU stress engine initialisation failed: "
                            + error.getMessage()
            );
        }

        this.available = success;
    }


    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Returns {@code true} if a GPU was successfully
     * initialised and is ready for stress testing.
     */
    public boolean isAvailable() {
        return available;
    }


    /**
     * Returns the name of the discovered GPU device,
     * or "none" if no GPU is available.
     */
    public String getDeviceName() {
        return deviceName;
    }


    /**
     * Generates GPU load at the given intensity for the
     * specified duration.
     *
     * This method blocks the calling thread for the
     * entire duration. It should be called from a
     * dedicated GPU stress thread.
     *
     * @param targetPercent Load intensity (0-100).
     *                      0 means idle.
     * @param duration      How long to stress the GPU.
     * @param stopRequested Shared flag to request early
     *                      termination.
     */
    public void runGpuLoad(

            int targetPercent,

            Duration duration,

            AtomicBoolean stopRequested

    ) throws InterruptedException {


        if (!available) {
            return;
        }


        long endNanos =

                System.nanoTime()

                        + duration.toNanos();


        if (targetPercent <= 0) {

            waitUntil(
                    endNanos,
                    stopRequested
            );

            return;
        }


        int matrixSize =
                selectMatrixSize(targetPercent);

        long sleepMs =
                selectSleepMs(targetPercent);

        int elementCount =
                matrixSize * matrixSize;


        /*
         * Fill input matrices with random-ish data.
         *
         * We reuse the pre-allocated host arrays but only
         * fill up to the needed element count.
         */
        fillMatrixData(elementCount);


        /*
         * Set up GPU buffers for this matrix size.
         */
        long bufferSizeBytes =
                (long) Sizeof.cl_float * elementCount;


        cl_mem stepBufferA = CL.clCreateBuffer(
                context,
                CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                bufferSizeBytes,
                Pointer.to(hostA),
                null
        );

        cl_mem stepBufferB = CL.clCreateBuffer(
                context,
                CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                bufferSizeBytes,
                Pointer.to(hostB),
                null
        );

        cl_mem stepBufferC = CL.clCreateBuffer(
                context,
                CL.CL_MEM_READ_WRITE,
                bufferSizeBytes,
                null,
                null
        );


        try {

            /*
             * Set kernel arguments.
             */
            CL.clSetKernelArg(
                    kernel, 0,
                    Sizeof.cl_mem,
                    Pointer.to(stepBufferA)
            );

            CL.clSetKernelArg(
                    kernel, 1,
                    Sizeof.cl_mem,
                    Pointer.to(stepBufferB)
            );

            CL.clSetKernelArg(
                    kernel, 2,
                    Sizeof.cl_mem,
                    Pointer.to(stepBufferC)
            );

            CL.clSetKernelArg(
                    kernel, 3,
                    Sizeof.cl_int,
                    Pointer.to(new int[]{matrixSize})
            );


            long[] globalWorkSize = new long[]{
                    matrixSize,
                    matrixSize
            };


            /*
             * Repeatedly execute the kernel until the
             * step duration expires.
             */
            while (

                    !stopRequested.get()

                            && !Thread.currentThread()
                                    .isInterrupted()

                            && System.nanoTime() < endNanos

            ) {

                CL.clEnqueueNDRangeKernel(
                        commandQueue,
                        kernel,
                        2,
                        null,
                        globalWorkSize,
                        null,
                        0,
                        null,
                        null
                );

                CL.clFinish(commandQueue);


                if (sleepMs > 0) {

                    long remaining =
                            endNanos - System.nanoTime();

                    if (remaining <= 0) {
                        break;
                    }

                    long actualSleep = Math.min(
                            sleepMs,
                            TimeUnit.NANOSECONDS
                                    .toMillis(remaining)
                    );

                    if (actualSleep > 0) {

                        TimeUnit.MILLISECONDS.sleep(
                                actualSleep
                        );
                    }
                }
            }

        } finally {

            /*
             * Release the per-step GPU buffers.
             */
            safeRelease(stepBufferA);
            safeRelease(stepBufferB);
            safeRelease(stepBufferC);
        }
    }


    /**
     * Releases all OpenCL resources.
     *
     * Call this once when the stress test is complete.
     */
    public void shutdown() {

        if (!available) {
            return;
        }

        try {

            if (kernel != null) {
                CL.clReleaseKernel(kernel);
            }

            if (program != null) {
                CL.clReleaseProgram(program);
            }

            if (commandQueue != null) {
                CL.clReleaseCommandQueue(commandQueue);
            }

            if (context != null) {
                CL.clReleaseContext(context);
            }

        } catch (Exception error) {

            System.err.println(
                    "Error during GPU engine shutdown: "
                            + error.getMessage()
            );
        }
    }


    // ============================================================
    // OPENCL INITIALISATION
    // ============================================================

    private boolean initialiseOpenCL() {


        CL.setExceptionsEnabled(true);


        // ----------------------------------------------------
        // Discover platforms
        // ----------------------------------------------------

        int[] platformCount = new int[1];

        CL.clGetPlatformIDs(
                0,
                null,
                platformCount
        );

        if (platformCount[0] == 0) {

            System.out.println(
                    "[GPU] No OpenCL platforms found."
            );

            return false;
        }


        cl_platform_id[] platforms =
                new cl_platform_id[platformCount[0]];

        CL.clGetPlatformIDs(
                platformCount[0],
                platforms,
                null
        );


        // ----------------------------------------------------
        // Find the first GPU device across all platforms
        // ----------------------------------------------------

        cl_platform_id selectedPlatform = null;
        cl_device_id selectedDevice = null;

        for (cl_platform_id platform : platforms) {

            int[] deviceCount = new int[1];

            try {

                CL.clGetDeviceIDs(
                        platform,
                        CL.CL_DEVICE_TYPE_GPU,
                        0,
                        null,
                        deviceCount
                );

            } catch (Exception ignored) {

                continue;
            }

            if (deviceCount[0] > 0) {

                cl_device_id[] devices =
                        new cl_device_id[deviceCount[0]];

                CL.clGetDeviceIDs(
                        platform,
                        CL.CL_DEVICE_TYPE_GPU,
                        deviceCount[0],
                        devices,
                        null
                );

                selectedPlatform = platform;
                selectedDevice = devices[0];

                break;
            }
        }


        if (selectedDevice == null) {

            System.out.println(
                    "[GPU] No OpenCL GPU device found."
            );

            return false;
        }


        this.device = selectedDevice;

        this.deviceName = getDeviceInfoString(
                selectedDevice,
                CL.CL_DEVICE_NAME
        );


        // ----------------------------------------------------
        // Create context
        // ----------------------------------------------------

        cl_context_properties contextProperties =
                new cl_context_properties();

        contextProperties.addProperty(
                CL.CL_CONTEXT_PLATFORM,
                selectedPlatform
        );

        this.context = CL.clCreateContext(
                contextProperties,
                1,
                new cl_device_id[]{selectedDevice},
                null,
                null,
                null
        );


        // ----------------------------------------------------
        // Create command queue
        // ----------------------------------------------------

        cl_queue_properties queueProperties =
                new cl_queue_properties();

        this.commandQueue = CL.clCreateCommandQueueWithProperties(
                context,
                selectedDevice,
                queueProperties,
                null
        );


        // ----------------------------------------------------
        // Compile kernel
        // ----------------------------------------------------

        this.program = CL.clCreateProgramWithSource(
                context,
                1,
                new String[]{KERNEL_SOURCE},
                null,
                null
        );

        CL.clBuildProgram(
                program,
                0,
                null,
                null,
                null,
                null
        );

        this.kernel = CL.clCreateKernel(
                program,
                "matMul",
                null
        );


        // ----------------------------------------------------
        // Pre-allocate host arrays for max matrix size
        // ----------------------------------------------------

        int maxElements = SIZE_MAX * SIZE_MAX;

        this.hostA = new float[maxElements];
        this.hostB = new float[maxElements];
        this.hostC = new float[maxElements];


        System.out.println(
                "[GPU] OpenCL GPU initialised: "
                        + deviceName
        );


        return true;
    }


    // ============================================================
    // LOAD SCALING HELPERS
    // ============================================================

    /**
     * Maps a target load percentage to a matrix dimension.
     */
    private static int selectMatrixSize(
            int targetPercent
    ) {

        if (targetPercent <= 30) {
            return SIZE_SMALL;
        }

        if (targetPercent <= 50) {
            return SIZE_MEDIUM;
        }

        if (targetPercent <= 70) {
            return SIZE_LARGE;
        }

        return SIZE_MAX;
    }


    /**
     * Maps a target load percentage to the sleep interval
     * (in milliseconds) between consecutive kernel executions.
     */
    private static long selectSleepMs(
            int targetPercent
    ) {

        if (targetPercent <= 30) {
            return SLEEP_LOW_MS;
        }

        if (targetPercent <= 50) {
            return SLEEP_MEDIUM_MS;
        }

        if (targetPercent <= 70) {
            return SLEEP_HIGH_MS;
        }

        return SLEEP_MAX_MS;
    }


    // ============================================================
    // MATRIX DATA FILL
    // ============================================================

    /**
     * Fills the host arrays with deterministic data.
     *
     * We use simple arithmetic to avoid the overhead of
     * a Random instance while still producing non-trivial
     * matrix content that prevents compiler optimisation.
     */
    private void fillMatrixData(int elementCount) {

        for (int i = 0; i < elementCount; i++) {

            hostA[i] =
                    (float) Math.sin(i * 0.01) + 0.5f;

            hostB[i] =
                    (float) Math.cos(i * 0.01) + 0.5f;
        }
    }


    // ============================================================
    // WAIT HELPER
    // ============================================================

    private static void waitUntil(

            long endNanos,

            AtomicBoolean stopRequested

    ) throws InterruptedException {


        while (!stopRequested.get()) {

            long remaining =
                    endNanos - System.nanoTime();

            if (remaining <= 0) {
                return;
            }

            TimeUnit.NANOSECONDS.sleep(
                    Math.min(
                            remaining,
                            TimeUnit.MILLISECONDS
                                    .toNanos(250)
                    )
            );
        }
    }


    // ============================================================
    // OPENCL UTILITY
    // ============================================================

    /**
     * Reads a string device info parameter.
     */
    private static String getDeviceInfoString(

            cl_device_id device,

            int paramName

    ) {

        long[] size = new long[1];

        CL.clGetDeviceInfo(
                device,
                paramName,
                0,
                null,
                size
        );

        byte[] buffer = new byte[(int) size[0]];

        CL.clGetDeviceInfo(
                device,
                paramName,
                buffer.length,
                Pointer.to(buffer),
                null
        );

        return new String(
                buffer, 0,
                Math.max(0, buffer.length - 1)
        ).trim();
    }


    /**
     * Safely releases an OpenCL memory object.
     */
    private static void safeRelease(cl_mem memObject) {

        if (memObject != null) {

            try {

                CL.clReleaseMemObject(memObject);

            } catch (Exception ignored) {
                // Best-effort cleanup.
            }
        }
    }
}
