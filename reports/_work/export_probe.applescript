with timeout of 120 seconds
 tell application "Microsoft Word"
  set d to document "render_probe.docx"
  repaginate d
  save as d file name "/Users/nguyenthenam/UTE-Tea/reports/_work/render_probe.pdf" file format format PDF
  close d saving no
 end tell
end timeout
