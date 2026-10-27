with timeout of 120 seconds
 tell application "Microsoft Word"
  set d to active document
  save as active document file name "Macintosh HD:Users:nguyenthenam:UTE-Tea:reports:_work:render_probe.pdf" file format format PDF
 end tell
end timeout
