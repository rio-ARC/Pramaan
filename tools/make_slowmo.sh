#!/bin/bash
ffmpeg -i in.mp4 -filter:v "setpts=2.0*PTS" -an out.mp4
