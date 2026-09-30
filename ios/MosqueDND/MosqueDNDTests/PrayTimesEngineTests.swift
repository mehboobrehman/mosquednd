import XCTest
@testable import MosqueDND

final class PrayTimesEngineTests: XCTestCase {

    func testKarachiCalculation() {
        // Karachi coordinates: 24.8607, 67.0011
        let lat = 24.8607
        let lon = 67.0011
        let tz = TimeZone(identifier: "Asia/Karachi")!
        
        // Use a fixed date: 2026-08-05
        var components = DateComponents()
        components.year = 2026
        components.month = 8
        components.day = 5
        components.timeZone = tz
        let date = Calendar.current.date(from: components)!
        
        let times = PrayTimesEngine.calculate(
            date: date,
            latitude: lat,
            longitude: lon,
            timeZone: tz,
            method: .karachi,
            asrMethod: .standard
        )
        
        XCTAssertNotNil(times[.fajr])
        XCTAssertNotNil(times[.sunrise])
        XCTAssertNotNil(times[.dhuhr])
        XCTAssertNotNil(times[.asr])
        XCTAssertNotNil(times[.maghrib])
        XCTAssertNotNil(times[.isha])
        
        // Basic sanity checks for Karachi on Aug 5
        // Fajr is usually around 04:30-05:00
        // Dhuhr is around 12:30
        // Maghrib is around 19:15
        
        print("Karachi Fajr: \(times[.fajr]!.formattedTime)")
        print("Karachi Dhuhr: \(times[.dhuhr]!.formattedTime)")
        print("Karachi Maghrib: \(times[.maghrib]!.formattedTime)")
        
        XCTAssertEqual(times[.dhuhr]?.formattedTime, "12:38")
    }
    
    func testHighLatitudeAngleBased() {
        // High latitude: London (51.5074, -0.1278)
        let lat = 51.5074
        let lon = -0.1278
        let tz = TimeZone(identifier: "Europe/London")!
        
        var components = DateComponents()
        components.year = 2026
        components.month = 6
        components.day = 21 // Summer solstice
        components.timeZone = tz
        let date = Calendar.current.date(from: components)!
        
        let times = PrayTimesEngine.calculate(
            date: date,
            latitude: lat,
            longitude: lon,
            timeZone: tz,
            method: .mwl,
            highLatitudeMethod: .angleBased
        )
        
        XCTAssertNotNil(times[.fajr])
        XCTAssertNotNil(times[.isha])
        
        print("London Fajr (Angle Based): \(times[.fajr]!.formattedTime)")
    }
}
