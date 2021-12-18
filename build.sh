#!/bin/bash

if ./gradlew assemble; then
	# TODO figure out why "untitled"
	cp build/bin/lucifer/releaseExecutable/lucifer.kexe ./lucifer
	cp build/bin/lucifer/debugExecutable/lucifer.kexe ./luciferDebug
else
	echo Build failed!
fi	
