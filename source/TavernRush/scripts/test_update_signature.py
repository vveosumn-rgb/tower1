#!/usr/bin/env python3
import unittest
from check_update_signature import compare
class UpdateGateTest(unittest.TestCase):
 def test_compatible(self):self.assertEqual(0,compare(('same',1013,['a']),('same',1014,['a']))[0])
 def test_other_package(self):self.assertEqual(2,compare(('old',1013,['a']),('new',1014,['a']))[0])
 def test_other_certificate(self):self.assertEqual(3,compare(('same',1013,['a']),('same',1014,['b']))[0])
 def test_lower_version(self):self.assertEqual(4,compare(('same',1013,['a']),('same',1012,['a']))[0])
 def test_equal_version(self):self.assertEqual(4,compare(('same',1013,['a']),('same',1013,['a']))[0])
 def test_multiple_signers(self):self.assertEqual(3,compare(('same',1013,['a','b']),('same',1014,['a']))[0])
if __name__=='__main__':unittest.main()
