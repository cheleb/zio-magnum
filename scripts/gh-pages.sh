#!/usr/bin/env bash

# Strict mode, fail on any error
set -e

export VERSION=`git describe --tags --abbrev=0 | sed "s/v//"`
echo "Documentation version: $VERSION"
sbt website
