#include <iostream>
#include <vector>
#include <utility>
#include <windows.h>
#include <fileapi.h>
#include <stdlib.h>
#include <sddl.h>

using namespace std;

/*
GetVolumePathName
GetFileSecurity
SetFileSecurity
GetFileAttributes
SetFileAttributes
DeleteFile
*/


/*
subst J: C:\

subst T: C:\temp\VirtualDriveTest
- owner.txt
- owner-link (symlink to owner.txt)
*/


void printErrorMessage() {
    cerr << "Last error code: " << GetLastError() << endl;
    wchar_t buf[256];
    FormatMessageW(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
        NULL, GetLastError(), MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
        buf, (sizeof(buf) / sizeof(wchar_t)), NULL);
    wcerr << buf << endl;
}

// https://docs.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getvolumepathnamew
void testVolumePathName(LPCWCH fileName) {
    cerr << "VolumePathName" << endl;
    WCHAR volume[_MAX_PATH];
    BOOL ret = GetVolumePathNameW(fileName, volume, _MAX_PATH);
    if (ret == 0) {
        printErrorMessage();
    }
    else {
        wcerr << "Volume: " << volume << endl;
        cerr << "Test succeeded" << endl;
    }
}


// https://docs.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getfileattributesw
void testGetSetFileAttributes(LPCWCH fileName) {
    cerr << "testGetSetFileAttributes" << endl;
    DWORD attr = GetFileAttributesW(fileName);
    if (attr == INVALID_FILE_ATTRIBUTES) {
        printErrorMessage();
        return;
    }

    if (!(SetFileAttributesW(fileName, attr | FILE_ATTRIBUTE_HIDDEN))) {
        cerr << "Failed to set hidden attribute" << endl;
        printErrorMessage();
        return;
    }
    DWORD newAttr = GetFileAttributesW(fileName);
    if (newAttr == INVALID_FILE_ATTRIBUTES) {
        cerr << "Failed to get attribute after setting hidden attr" << endl;
        printErrorMessage();
        return;
    }
    if (!(newAttr & FILE_ATTRIBUTE_HIDDEN)) {
        cerr << "Set attribute wasn't successful. Why does the file not have the hidden attribute?" << endl;
        return;
    }
    if (!(SetFileAttributesW(fileName, attr))) {
        cerr << "Failed to restore attribute" << endl;
        return;
    }
    newAttr = GetFileAttributes(fileName);
    if (newAttr == INVALID_ATOM) {
        cerr << "Failed to get attribute after restoring original attr" << endl;
        return;
    }
    if (newAttr != attr) {
        cerr << "Attribute is not the same as original after restoring" << endl;
        return;
    }
    cerr << "Test succeeded" << endl;
}

void testSetFileSecurity(LPCWCH fileName) {
    cerr << "SetFileSecurity" << endl;
    DWORD lengthNeeded = 0;
    BOOL ret = GetFileSecurityW(
        fileName,
        OWNER_SECURITY_INFORMATION,
        nullptr,
        0,
        &lengthNeeded
    );

    if (ret == 0) {
        if (GetLastError() != ERROR_INSUFFICIENT_BUFFER) {
            cerr << "GetFileSecurity failed" << endl;
            printErrorMessage();
            return;
        }
    }
    else {
        cerr << "Why did GetFileSecurityW succeed?" << endl;
        return;
    }

    cout << lengthNeeded << endl;
    PSECURITY_DESCRIPTOR descriptor = (PSECURITY_DESCRIPTOR)malloc(lengthNeeded);
    ret = GetFileSecurityW(
        fileName,
        OWNER_SECURITY_INFORMATION,
        descriptor,
        lengthNeeded,
        &lengthNeeded
    );
    if (ret == 0) {
        printErrorMessage();
        cerr << "GetFileSecurityW failed" << endl;
        return;
    }

    ret = SetSecurityDescriptorOwner(
       descriptor,
       nullptr,
       true
    );

    if (ret == 0) {
       printErrorMessage();
       cerr << "Error manipulating returned security descriptor" << endl;
       return;
    }

    ret = SetFileSecurityW(
        fileName,
        OWNER_SECURITY_INFORMATION,
        descriptor
    );

    if (ret == 0) {
        printErrorMessage();
        cerr << "Error setting security descriptor" << endl;
    }
}

// https://docs.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-getfilesecuritya
void testGetFileSecurity(LPCWCH fileName) {
    cerr << "GetFileSecurity" << endl;
    DWORD lengthNeeded = 0;
    BOOL ret = GetFileSecurityW(
        fileName,
        OWNER_SECURITY_INFORMATION,
        nullptr,
        0,
        &lengthNeeded
    );

    if (ret == 0) {
        if (GetLastError() != ERROR_INSUFFICIENT_BUFFER) {
            printErrorMessage();
            cerr << "GetFileSecurity failed" << endl;
            return;
        }
    }
    else {
        cerr << "GetFileSecurityW should have failed because we are only getting the buffer size" << endl;
        return;
    }

    PSECURITY_DESCRIPTOR descriptor = (PSECURITY_DESCRIPTOR)malloc(lengthNeeded);
    ret = GetFileSecurityW(
        fileName,
        OWNER_SECURITY_INFORMATION,
        descriptor,
        lengthNeeded,
        &lengthNeeded
    );
    if (ret == 0) {
        cerr << "GetFileSecurityW failed" << endl;
        printErrorMessage();
        return;
    }
    
    BOOL ownerDefaulted;
    PSID sid;
    ret = GetSecurityDescriptorOwner(descriptor, &sid, &ownerDefaulted);

    if (ret == 0) {
        printErrorMessage();
        cerr << "GetSecurityDescriptorOwner failed" << endl;
        return;
    }

    WCHAR* name = nullptr, * domain = nullptr;
    unsigned long nameSize = 0, domainSize = 0;
    SID_NAME_USE nameUse;
    ret = LookupAccountSidW(
        nullptr,
        sid,
        name,
        &nameSize,
        domain,
        &domainSize,
        &nameUse
    );

    name = (WCHAR*) malloc(nameSize * sizeof(TCHAR));
    domain = (WCHAR*)malloc(domainSize * sizeof(TCHAR));
    ret = LookupAccountSidW(
        nullptr,
        sid,
        name,
        &nameSize,
        domain,
        &domainSize,
        &nameUse
    );

    wcerr << "Owner name: " << name << endl;
    wcerr << "Domain name: " << domain << endl;
    cerr << "Test succeeded" << endl;
}

void testDeleteRestoreFile(LPCWCH fileName) {
    cerr << "testDeleteRestore" << endl;
    LPCWCH backup = L"C:\\temp\\testBackup";
    BOOL ret = CopyFileW(
        fileName,
        backup,
        false // bailIfExists
    );
    if (!ret) {
        printErrorMessage();
        cerr << "Error backing up file before deleting" << endl;
        return;
    }

    ret = DeleteFile(fileName);
    if (!ret) {
        printErrorMessage();
        cerr << "Error deleting original file" << endl;
        return;
    }

    ret = CopyFileW(
        backup,
        fileName,
        false
    );
    if (!ret) {
        printErrorMessage();
        cerr << "Error restoring the deleted file" << endl;
        return;
    }

    ret = DeleteFile(backup);
    if (!ret) {
        printErrorMessage();
        cerr << "Error deleting backed up file" << endl;
        return;
    }
    cerr << "Test succeeded" << endl;
}

void testDeleteRestoreFolder(LPCWCH fileName) {
    cerr << "testDeleteRestore" << endl;
    LPCWCH backup = L"C:\\temp\\testBackup";
    BOOL ret = CopyFileW(
        fileName,
        backup,
        false // bailIfExists
    );
    if (!ret) {
        printErrorMessage();
        cerr << "Error backing up file before deleting" << endl;
        return;
    }

    ret = DeleteFile(fileName);
    if (!ret) {
        printErrorMessage();
        cerr << "Error deleting original file" << endl;
        return;
    }

    ret = CopyFileW(
        backup,
        fileName,
        false
    );
    if (!ret) {
        printErrorMessage();
        cerr << "Error restoring the deleted file" << endl;
        return;
    }

    ret = DeleteFile(backup);
    if (!ret) {
        printErrorMessage();
        cerr << "Error deleting backed up file" << endl;
        return;
    }
    cerr << "Test succeeded" << endl;
}

void test(string testName, LPCWCH filename) {
    cerr << endl << endl;
    cerr << "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++" << endl << endl;
    cerr << "Testing: " << testName << endl << endl;
    cerr << "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++" << endl << endl;

    testVolumePathName(filename);
    cerr << "------------------" << endl;

    testGetFileSecurity(filename);
    cerr << "------------------" << endl;

    testSetFileSecurity(filename);
    cerr << "------------------" << endl;

    testGetFileSecurity(filename);
    cerr << "------------------" << endl;

    testGetSetFileAttributes(filename);
    cerr << "------------------" << endl;

    testDeleteRestoreFile(filename);
}

using Test = pair<string, LPCWCH>;

int main()
{
    vector<vector<Test>> tests {
        {
            // Full path
            {"Root C: drive", L"C:\\"},
            // Virtual drive
            {"J: drive that is mapped to C:", L"J:\\"}
        },

        {
            {"Full path to a folder", L"C:\\temp\\VirtualDriveTest"},
            {"T: drive substituted to that folder", L"T:\\"},
        },

        {
            {"Full path to a file", L"C:\\temp\\VirtualDriveTest\\file1.txt"},
            {"Path to that file using substituted T: drive", L"T:\\file1.txt"},
        },

        {
            {"Full path to symlinked (soft) file", L"C:\\temp\\VirtualDriveTest\\file1Link"},
            {"Symlinked file in substituted T: drive", L"T:\\file1Link"},
        }
    };

    for (auto &t : tests) {
        for (auto& [testName, pathName] : t) {
            test(testName, pathName);
        }
    }
}
