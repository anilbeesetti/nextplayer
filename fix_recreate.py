import re

with open("feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt", "r") as f:
    content = f.read()

replacement = """        // Clear the video surface before releasing to avoid stale references blocking the new player
        currentPlayer.clearVideoSurface()
        // 3. Release old player
        currentPlayer.release()"""

content = re.sub(r'// 3\. Release old player\s*currentPlayer\.release\(\)', replacement, content)

with open("feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt", "w") as f:
    f.write(content)
