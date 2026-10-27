with timeout of 120 seconds
 tell application "Microsoft Word"
  set d to document "render_probe.docx"
  save d in POSIX file "/Users/nguyenthenam/UTE-Tea/reports/_work/render_probe_standard.pdf" as "PDF"
 end tell
end timeout
