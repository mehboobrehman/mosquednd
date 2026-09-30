import XCTest
@testable import MosqueDND

final class QiblaEngineTests: XCTestCase {

    func testKarachiToKaabaBearing() {
        // Karachi: 24.8607° N, 67.0011° E
        // Kaaba: 21.4225° N, 39.8261° E
        let bearing = QiblaEngine.calculateBearing(lat1: 24.8607, lon1: 67.0011, lat2: 21.4225, lon2: 39.8261)
        
        // Expected ~267° (West by South-West)
        XCTAssertEqual(bearing, 267.0, accuracy: 1.0)
    }

    func testKaabaToKarachiBearing() {
        // Reverse bearing
        let bearing = QiblaEngine.calculateBearing(lat1: 21.4225, lon1: 39.8261, lat2: 24.8607, lon2: 67.0011)
        
        // Expected ~75° (East by North-East)
        XCTAssertEqual(bearing, 75.0, accuracy: 1.0)
    }
    
    func testEquatorBearing() {
        // Point on Equator to another point on Equator
        let bearing = QiblaEngine.calculateBearing(lat1: 0.0, lon1: 0.0, lat2: 0.0, lon2: 10.0)
        XCTAssertEqual(bearing, 90.0, accuracy: 0.001)
    }
    
    func testGreenwichBearing() {
        // From London to Equator/Greenwich
        let bearing = QiblaEngine.calculateBearing(lat1: 51.5, lon1: 0.0, lat2: 0.0, lon2: 0.0)
        XCTAssertEqual(bearing, 180.0, accuracy: 0.5)
    }
    
    func testSamePointReturnsZero() {
        let bearing = QiblaEngine.calculateBearing(lat1: 21.4225, lon1: 39.8261, lat2: 21.4225, lon2: 39.8261)
        XCTAssertEqual(bearing, 0.0, accuracy: 0.001)
    }
    
    func testDueNorthReturnsSouth() {
        let bearing = QiblaEngine.calculateBearing(lat1: 30.0, lon1: 39.8261, lat2: 21.4225, lon2: 39.8261)
        XCTAssertEqual(bearing, 180.0, accuracy: 0.5)
    }
    
    func testDueEastReturnsWest() {
        let bearing = QiblaEngine.calculateBearing(lat1: 0.0, lon1: 10.0, lat2: 0.0, lon2: 0.0)
        XCTAssertEqual(bearing, 270.0, accuracy: 0.001)
    }
    
    func testNearNorthPoleReturnsValidBearing() {
        let bearing = QiblaEngine.calculateBearing(lat1: 89.9, lon1: 0.0, lat2: 21.4225, lon2: 39.8261)
        XCTAssertGreaterThan(bearing, 0.0)
        XCTAssertLessThan(bearing, 360.0)
        XCTAssertEqual(bearing, 140.0, accuracy: 10.0)
    }
    
    func testAntipodeOfKaabaReturnsValidBearing() {
        // Antipode of Kaaba: lat=-21.4225°, lon=-140.1739°
        let bearing = QiblaEngine.calculateBearing(lat1: -21.4225, lon1: -140.1739, lat2: 21.4225, lon2: 39.8261)
        XCTAssertFalse(bearing.isNaN)
        XCTAssertTrue(bearing.isFinite)
        XCTAssertTrue(bearing >= 0.0 && bearing < 360.0)
    }
}
