Final Project for CSC-376, program parses a JSON response received from caltech's exoplanet database.
Data consists of planets' eccentricity, mass in jupiters, distance from the sun, etc.

Data is stored using Google's Gson library in their custom data structures.

Program is meant to run as a subprocess to a simple server and parse commands from standard input from parent process.

Program parses the commands and looks at the data finding the X and Y axis data points and stores them in a text file to be read by gnuplot.

Gnuplot is then opened as another process under this program to then data in the data points and plot them to a 2-D graph.

Once gnuplot finishes this task, a JSON command is sent to the standard output so the parent process (server) can verify the graph was made.

The server then sends the graph to a simple webpage where it can be displayed with all the data specified.
