#!/bin/bash

if ./gradlew assemble; then
	# TODO figure out why "untitled"
	cp build/bin/lucifer/releaseExecutable/untitled.kexe ./lucifer
	cp build/bin/lucifer/debugExecutable/untitled.kexe ./luciferDebug
else
	echo Build failed!
fi	
