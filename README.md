# lucifer

The goal of this tool is to parse lsof output, and provide useful summarization. Right now all it does is provide list of processes ordered by the number of open files, but once the basics are stabilized additional functionality will be added.

## building

```
./build.sh
```

## usage

```
lsof -F | ./lucifer
```

## status

Beta. Produces basic output.

## roadmap

* Formatting for main summary by process
 * ansi colors  
 * --detail flag to list all files under each process
 * resolve user ids
* Summarize by file type
* Summarize by user
* Get full command (and other metadata) from ps

# kotlin-native

This project is an exploration of kotlin-native, which is basically functional, but has some real warts. The biggest that I encountered is that readLine() does not split on newline (0xA); after spending a couple of hours trying to massage the broken output I eventually realized that I could just call C's stdio `fgets` function, which reads a line. Nice. 
