# The base install's Lib/pydoc.py is a 17-byte stub ("help([1, 2, 3])"), so `import pydoc` fails, and
# scipy._lib._docscrape imports it at load time. Register a minimal stand-in for this venv only.
# scipy only calls pydoc.getdoc; this is CPython 3.11's getdoc with inspect.getdoc in place of its private _getdoc.
import inspect
import re
import sys
import types


def getdoc(object):
    result = inspect.getdoc(object) or inspect.getcomments(object)
    return result and re.sub('^ *\n', '', result.rstrip()) or ''


_m = types.ModuleType("pydoc", "venv stand-in: getdoc only (base Lib/pydoc.py is broken)")
_m.getdoc = getdoc
sys.modules["pydoc"] = _m
