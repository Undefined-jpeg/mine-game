#!/bin/bash

# Get the current folder path
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "---------------------------------------"
echo "🚧  STEP 1: Compiling Java Code..."
echo "---------------------------------------"

# Compile everything in this folder
cd "$DIR"
javac *.java

# Check if compile failed (exit code is not 0)
if [ $? -ne 0 ]; then
  echo "❌ Compilation ERROR! Please fix the code errors above."
  exit 1
fi

echo "✅ Compilation Successful!"
echo ""

echo "---------------------------------------"
echo "🚀  STEP 2: Launching Server..."
echo "---------------------------------------"
# Opens a new Terminal window to run the Server
osascript -e "tell app \"Terminal\" to do script \"cd '$DIR'; java GameServer\""

echo "Waiting 2 seconds for server to wake up..."
sleep 2

echo "---------------------------------------"
echo "🎮  STEP 3: Launching Client..."
echo "---------------------------------------"
# Opens a new Terminal window to run the Client
osascript -e "tell app \"Terminal\" to do script \"cd '$DIR'; java GameClient\""

echo "✅ Done! Check the pop-up windows."