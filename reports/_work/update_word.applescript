set inputFile to POSIX file "/Users/nguyenthenam/UTE-Tea/reports/Bao_Cao_UTE_Tea.docx" as alias
with timeout of 600 seconds
 tell application "Microsoft Word"
  open inputFile
  delay 5
  set d to document "Bao_Cao_UTE_Tea.docx"
  set totalFields to count of fields of d
  set successCount to 0
  repeat with ff in (get every field of d)
   try
    if update field ff then set successCount to successCount + 1
   end try
  end repeat
  repaginate d
  save d
  close d saving yes
  return "fields=" & totalFields & ", updated=" & successCount
 end tell
end timeout
