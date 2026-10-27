set inputFile to POSIX file "/Users/nguyenthenam/UTE-Tea/reports/_work/render_probe.docx" as alias
with timeout of 120 seconds
 tell application "Microsoft Word"
  open inputFile
  delay 3
  set namesList to name of every document
  return namesList
 end tell
end timeout
