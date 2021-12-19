# lucifer 0.1

The goal of this tool is to parse **lsof** output, and provide useful summarization. 

## reports

It currently provides 3 reports:

### OPEN FILES BY PROCESS
This is a list of all processes (with associated metadata), along with the count of files opened by that process. Note that child processes may have files in common with each other.

### OPEN FILES BY TYPE BY USER
A summary of the number of files, by type, by user.

### INTERNET CONNECTIONS BY USER
A list of all UDP and TCP information, by user. Some columns are truncated to give as much room to the connection detail without wrapping.

## building

```
./build.sh
```

## usage examples

Lucifer parses the output of lsof -F. Any other argument provided to lsof may result in the parsing crashing.

```
lsof -F | ./lucifer

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

* --parent flag to order by parent / parent tree
* Smart coloring in the extra reports
* Get full command (and other metadata) from ps
* Somehow match colors to user terminal (right now assumes dark background)
* ncurses based UI for browsing different reports / detail view

# kotlin-native

This project is an exploration of kotlin-native, which is basically functional, but has some real warts (listed here). 

* The biggest that I encountered is that readLine() does not split on newline (0xA); after spending a couple of hours trying to massage the broken output I eventually realized that I could just call C's stdio `fgets` function, which reads a line. Nice. 
* Method references (e.g. ::foo) cause the compiler to crash.
* Output binary size is very large. This alone is probably enough reason to port this to Go or Rust once it's useful enough.

# FAQ

## What's with the name?

Bad pun. Lucifer = lsof-er. 
