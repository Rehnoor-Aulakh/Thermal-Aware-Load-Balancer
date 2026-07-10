import os
import subprocess
import sys
from pathlib import Path


VENV_NAME = ".venv"
REQUIREMENTS_FILE = "requirements.txt"


def run(command):
    print(f"\n>>> Running: {' '.join(map(str, command))}\n")
    subprocess.run(command, check=True)


def main():
    project_dir = Path(__file__).resolve().parent

    venv_dir = project_dir / VENV_NAME
    requirements_path = project_dir / REQUIREMENTS_FILE

    # Check requirements.txt
    if not requirements_path.exists():
        print(f"ERROR: {REQUIREMENTS_FILE} was not found.")
        print(f"Expected location: {requirements_path}")
        sys.exit(1)

    # Create virtual environment
    if not venv_dir.exists():
        print(f"Creating virtual environment: {venv_dir}")
        run([
            sys.executable,
            "-m",
            "venv",
            str(venv_dir)
        ])
    else:
        print(f"Virtual environment already exists: {venv_dir}")

    # Paths inside Linux virtual environment
    python_path = venv_dir / "bin" / "python"
    pip_path = venv_dir / "bin" / "pip"

    # Upgrade pip
    print("\nUpgrading pip...")
    run([
        str(python_path),
        "-m",
        "pip",
        "install",
        "--upgrade",
        "pip"
    ])

    # Install dependencies
    print("\nInstalling packages from requirements.txt...")
    run([
        str(pip_path),
        "install",
        "-r",
        str(requirements_path)
    ])

    print("\n==========================================")
    print("Environment setup completed successfully!")
    print("==========================================")
    print(f"Python: {python_path}")
    print(f"Environment: {venv_dir}")

    # Open a new shell with the virtual environment activated
    print("\nOpening activated virtual environment...")
    print("Type 'exit' when you want to leave the environment.\n")

    activate_script = venv_dir / "bin" / "activate"

    subprocess.run([
        "bash",
        "--rcfile",
        str(activate_script)
    ])


if __name__ == "__main__":
    main()