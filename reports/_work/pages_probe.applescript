set inputFile to POSIX file "/Users/nguyenthenam/UTE-Tea/reports/_work/render_probe.docx" as alias
set outputFile to POSIX file "/Users/nguyenthenam/UTE-Tea/reports/_work/render_probe_pages.pdf"
with timeout of 120 seconds
 tell application "Pages"
  set d to open inputFile
  export d to outputFile as PDF
  close d saving no
 end tell
end timeout
