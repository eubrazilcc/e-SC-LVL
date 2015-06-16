#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}")" && pwd )"
LIBDIR=$(dirname $DIR)

wine $LIBDIR/win-x86/megacc.exe $*
