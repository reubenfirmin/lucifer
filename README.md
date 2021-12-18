# lucifer

The goal of this tool is to parse lsof output, and provide useful summarization. Right now all it does is provide a ranked list of files by the number of open files, but once the basics are stabilized additional functionality will be added.

## building

```
./build.sh
```

## usage

```
lsof -F | ./lucifer
```

## status

Alpha. It works for the most part, but results should be taken to be approximate.

## roadmap

* Fix string parsing issues to ensure no data is lost
* Summarize by file type
* Resolve user ids

# kotlin-native

This project is an exploration of kotlin-native, which is basically functional, but has some real warts. The biggest of which (for the sake of this project) is that readLine() does not split on newline (0xA), as it does on a JVM platform, so the code is jumping through hoops to make sure data isn't lost. There appears to be a further bug in that readLine is losing some values - but that's currently being worked on.
