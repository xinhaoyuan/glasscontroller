import sys
import os
import re
import dbus
import urllib
from os import environ

line_with_tag = re.compile("^(-?[0-9]+) +([^ ].*)\n");

bus = dbus.SessionBus()
proxy = bus.get_object("org.freedesktop.DBus",  "/")
dbusif = dbus.Interface(proxy, "org.freedesktop.DBus")
okularif = None
okular = None
for iface in dbusif.ListNames():
    if "okular" in iface:
        okularif = iface
        break

if (okularif is None):
    sys.stderr.write("Cannot find okular")
    sys.exit(-1)

proxy = bus.get_object(okularif, "/okular")
okular = dbus.Interface(proxy, "org.kde.okular")

prefix = None
try:
    prefix = environ["GC_PRESENTATION_NOTE_PREFIX"]
except Exception:
    prefix = ""

while True:
    line = sys.stdin.readline();
    if len(line) == 0: break
    m = line_with_tag.match(line)
    if m is None:
        continue
    tag = m.group(1)
    cmd = m.group(2).split();

    if cmd[0] == "prev":
        okular.slotPreviousPage()
    elif cmd[0] == "next":
        okular.slotNextPage();
    elif cmd[0] == "skip" and len(cmd) > 1:
        page = okular.currentPage() + int(cmd[1])
        if page > okular.pages(): page = okular.pages()
        if page < 1: page = 1
        print page
        okular.goToPage(page);
    else:
        continue
        
    cur = int(okular.currentPage())
    hint = ""
    try:
        fn = prefix + str(cur)
        if os.path.isfile(fn):
            f = open(fn, "r")
            hint = f.read()
        else:
            hint = ""
    except Exception as x:
        print x
        hint = ""
    
    print tag + "&" + urllib.quote(hint) + "&" + urllib.quote("slide " + str(cur))
