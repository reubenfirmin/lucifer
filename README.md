# lucifer

The goal of this tool is to parse lsof output, and provide useful summarization. Right now all it does is provide a list of processes ordered by the number of open files. More in progress.

## building

```
./build.sh
```

## usage

```
lsof -F | ./lucifer
```

### arguments

```
--err send input from stdin through to stderr
```

## status

Beta.

## roadmap

* Formatting for main summary by process
  * ansi colors (/)
  * --detail flag to list all files under each process
  * resolve user ids (/)
* --no-format flag to turn off ansi colors
* --parent flag to order by parent
* Summarize by file type
* Summarize by user
* Get full command (and other metadata) from ps
* Somehow match colors to user terminal (right now assumes dark background)

# kotlin-native

This project is an exploration of kotlin-native, which is basically functional, but has some real warts (listed here). 

* The biggest that I encountered is that readLine() does not split on newline (0xA); after spending a couple of hours trying to massage the broken output I eventually realized that I could just call C's stdio `fgets` function, which reads a line. Nice. 
* Method references (e.g. ::foo) cause the compiler to crash.
* Output binary size is very large. This alone is probably enough reason to port this to Go or Rust once it's useful enough.

