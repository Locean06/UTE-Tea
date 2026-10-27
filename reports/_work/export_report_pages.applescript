set inputFile to POSIX file "/Users/nguyenthenam/UTE-Tea/reports/Bao_Cao_UTE_Tea.docx" as alias
set outputFile to POSIX file "/Users/nguyenthenam/UTE-Tea/reports/_work/Bao_Cao_UTE_Tea_pages.pdf"
with timeout of 600 seconds
 tell application "Pages"
  set d to open inputFile
  export d to outputFile as PDF
  close d saving no
 end tell
end timeout
