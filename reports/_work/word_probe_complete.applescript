set inputFile to POSIX file "/Users/nguyenthenam/UTE-Tea/reports/_work/render_probe.docx" as alias
set outputFile to (POSIX file "/Users/nguyenthenam/UTE-Tea/reports/_work/render_probe.pdf") as text
with timeout of 120 seconds
 tell application "Microsoft Word"
  open inputFile
  delay 3
  set d to active document
  repaginate d
  save as d file name outputFile file format format PDF
  close d saving no
 end tell
end timeout
