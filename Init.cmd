mkdir C:\temp\VirtualDriveTest
mkdir C:\temp\VirtualDriveTest\folder

echo "Virtual drive test 1" > C:\temp\VirtualDriveTest\file1.txt
echo "Virtual drive test 2" > C:\temp\VirtualDriveTest\folder\file2.txt

mklink C:\temp\VirtualDriveTest\file1Link C:\temp\VirtualDriveTest\file1.txt
mklink /D C:\temp\VirtualDriveTest\folderLink C:\temp\VirtualDriveTest\folder
