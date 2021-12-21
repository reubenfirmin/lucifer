# lucifer 0.5

The goal of this tool is to parse **lsof** output, and to provide useful summarization. PRs welcomed.

## reports

It currently provides 3 reports:

### OPEN FILES BY PROCESS
This is a list of all processes (with associated metadata), along with the count of files opened by that process. Note that child processes may have files in common with each other.

The top 5 most common parent PIDs are highlighted so that you can quickly see groups of associated processes.

### OPEN FILES BY TYPE BY USER
A summary of the number of files, by type, by user.

### INTERNET CONNECTIONS BY USER
A list of all UDP and TCP connections, by user. Some columns may be hidden in narrow terminals.

## building

```
./build.sh
```

## usage examples

Lucifer parses the output of lsof -F. Any other argument provided to lsof may result in the parsing crashing.

```
sudo lsof -F | ./lucifer

OR

sudo lsof -F > detail.txt
cat detail.txt | lucifer
cat detail.txt | lucifer --process 123 --process=5233
```

### optional arguments

```
--err           : send input from stdin through to stderr
--noformat      : turn off color highlighting
--process={pid} : full report on a specific process (may be repeated, e.g. --process=1 --process=2)
```

## status

Beta.

## roadmap

* smart coloring
  * coloring in network report by common host / open ports
  * coloring in open files report by users with most open files?
  * smart coloring in the reports to highlight things worth noticing
  * color coding for all ranges (generalize logic)
* --parent flag to order by parent / parent tree
* network report improvements
  * extended network stats using ss -tuiOp and ss -tuiOpl
  * highlight listening ports
* somehow match colors to user terminal (right now assumes dark background)
* ncurses based UI for browsing different reports / detail view

# kotlin-native

This project is an exploration of kotlin-native, which is basically functional, but has some real warts (listed here). 

* The biggest that I encountered is that readLine() does not split on newline (0xA); after spending a couple of hours trying to massage the broken output I eventually realized that I could just call C's stdio `fgets` function, which reads a line. Nice. 
* Method references (e.g. ::foo) cause the compiler to crash.
* Output binary size is very large. This alone is probably enough reason to port this to Go or Rust once it's useful enough.
* I couldn't get tests to work under Intellij (yet) so there are none, so far.

# FAQ

## What's with the name?

Bad pun. Lucifer = lsof-er. 
