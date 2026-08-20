package com.rehnoor.backendserveragent.controller;

import com.rehnoor.backendserveragent.dto.TargetLoadRequest;
import com.rehnoor.backendserveragent.service.StressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/stress")
@CrossOrigin(origins = "*")
public class StressController {

    private final StressService stressService;

    public StressController(StressService stressService){
        this.stressService = stressService;
    }

    @PostMapping("/target-load")
    public ResponseEntity<?> setTargetLoad(@RequestBody TargetLoadRequest request){
        stressService.setTargetLoad(request.targetLoad());
        return ResponseEntity.ok(Map.of("status", "success", "targetLoad", request.targetLoad()));
    }

    @PostMapping("/load/{load}")
    public ResponseEntity<String> setLoad(@PathVariable int load){
        stressService.setTargetLoad(load);
        return ResponseEntity.ok("Target load changed to "+load+"%");
    }

    @GetMapping("/target-load")
    public ResponseEntity<?> getTargetLoad(){
        return ResponseEntity.ok(Map.of("targetLoad", stressService.getTargetLoad()));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(){
        return ResponseEntity.ok(Map.of(
                "targetLoad", stressService.getTargetLoad(),
                "activeWorkers", stressService.getActiveWorkerCount(),
                "expectedWorkers", stressService.getExpectedWorkerCount(),
                "poolAlive", stressService.isPoolAlive()
        ));
    }

}
