package io.realm

import io.realm.model.Person
import io.realm.model.PersonProxy
import platform.Foundation.NSFileManager
import platform.Foundation.temporaryDirectory

actual object TestUtils {
    actual fun realmDefaultDirectory(): String {
//        NSString *path = [[NSBundle bundleForClass:[self class]] pathForResource:@"myResource" ofType:@"someType"];

        // Test case -> path inside "UnitTests" folder in the project directory
//        NSString *directory = [[NSFileManager defaultManager] currentDirectoryPath];
//        NSString *path = [directory stringByAppendingPathComponent:@"UnitTests/"];
//        path = [path stringByAppendingPathComponent:@"Test.sqlite"];
        println(" NSFileManager.defaultManager.currentDirectoryPath = ${NSFileManager.defaultManager.currentDirectoryPath}")
        println("NSFileManager.defaultManager.temporaryDirectory = ${NSFileManager.defaultManager.temporaryDirectory.absoluteString}")
        return NSFileManager.defaultManager.currentDirectoryPath
    }

    actual fun getPerson(): Person {
        return PersonProxy()
    }
}