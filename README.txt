CI REPAIR PACKAGE

Copy both folders to the root of the GitHub repository:
  .github/workflows/build-debug-apk.yml
  scripts/validate_project.py

The workflow will run the validator when present and safely skip it if the script is accidentally omitted.
