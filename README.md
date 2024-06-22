# Disc Downloader
A spigot plugin for downloading audio from youtube to minecraft discs.  

## /disc Command
  
`/disc add [Youtube link or video ID] [New name (optional)]`  
Alias : `/disc a`  
Downloads the audio from the given Youtube video to the server. 
Video ID is the string after the "watch?v=" in the link. 

`/disc give [Name]`  
Alias : `/disc g`  
Gives the disc that has the given name to the player.
  
`/disc [Youtube link or video ID] [New name (optional)]`  
Adds the audio and gives the disc to the player.

`/disc reload`  
Alias : `/disc r`  
Reloads the player's resource pack with latest version.  
**This command must be executed in order for changes (Such as new discs) to take effect!**  

`/disc list`  
Alias : `/disc l`  
Lists all of the audio currently downloaded on the server.  

`/disc remove [Name]`  
Alias : `/disc delete`, `/disc d`  
Removes audio with the given name.  

`/disc search [Query] [Download top (true/false) (optional)]`  
Alias : `/disc s`  
Searches Youtube with the given query.  
If `Download top` is true, the first result will be added to the server.  
If it is false or absent, a list of search results will be presented to the player.

`/disc purgeAll`  
Deletes all disc audio from the server.  
Player must be Op.  
