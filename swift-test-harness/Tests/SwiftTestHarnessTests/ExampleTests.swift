#if canImport(Testing)
import Testing
import CandleUg

@Suite("CandleUg Swift Export Tests")
struct CandleUgExportTests {
    @Test("CandleUg swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        #expect(Bool(true), "CandleUg swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import CandleUg

final class CandleUgExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "CandleUg swift module imported cleanly")
    }
}
#endif
