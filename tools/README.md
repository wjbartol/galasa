# Tools

These are tools intended for users of this project.

All tools respond to the --help parameter, which lists how to use the tool, so if in doubt about parameters, run that and use the help provided.

## build-locally.sh
Builds all of the code in this repository.

This is useful if you are developing the code on an isolated machine, and want to know if you have compiler breaks with the rest of the Java code in this repository.

By default, it builds everything.
You can limit what gets built by specifying which 'module' in the chain you want to start building from.

## list_dependencies.sh
Lists all the compile-time and run-time dependencies for all the project code.
