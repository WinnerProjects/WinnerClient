# Bridge JVM tests (no dependencies, JDK 21)

A = <SpotifyBridge APK repo>/wc/android-bridge/app/src/main/java/com/winnerclient/spotifybridge
M = <WinnerClient>/src/main/java/com/winnerclient/fabric/media

mkdir build
javac -d build $A/protocol/*.java $A/MediaState.java $A/BridgeServer.java \
  $M/bridge/*.java $M/AndroidMediaProvider.java $M/MediaProvider.java $M/MediaSnapshot.java BridgeTests.java
java -cp build BridgeTests      # expected last line: RESULT: 58 passed, 0 failed
