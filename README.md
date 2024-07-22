# Disc Downloader
A spigot plugin for downloading audio from youtube to minecraft discs.  

## /disc Command
  
`/disc add [Youtube link or video ID] [New name (optional)]`  
Alias : `/disc a`  
Downloads the audio from the given Youtube video to the server. 
Video ID is the string after the "watch?v=" in the link. 
Server must be restarted for changes to take effect.  

`/disc [Name]`  
Alias : `/disc give`, `/disc g` 
Gives the disc that has the given name to the player.
If config option `require-holding-disc` is true, the player must be holding a disc for the audio to be added to.  

`/disc list [Filter (optional)]`  
Alias : `/disc l`  
Lists all of the audio currently downloaded on the server that contain the filter (if given).  
Click on a song to give it to the player.  
Click on the trash button to delete the audio.  

`/disc remove [Name]`  
Alias : `/disc delete`, `/disc d`  
Removes audio with the given name.  

`/disc search [Query]`  
Alias : `/disc s`  
Searches Youtube with the given query.  
Click on a name to download it.  
Click on an ID to copy it for use in `/disc add`  

`/disc purgeAll`  
Deletes all disc audio from the server.  
Player must be Op.  
  
## Config Options
  
`max-song-length-seconds` (int), default value: 360 - The maximum length of audio that can be downloaded.  
`require-holding-disc` (bool), default value: true - Whether or not the player has to be holding a disc to put the audio on.  
