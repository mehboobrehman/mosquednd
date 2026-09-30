import Foundation

/// Engine to calculate the Qibla bearing using the Great Circle formula.
struct QiblaEngine {
    
    /// Calculates the initial bearing from point 1 to point 2.
    ///
    /// - Parameters:
    ///   - lat1: Latitude of point 1 in degrees.
    ///   - lon1: Longitude of point 1 in degrees.
    ///   - lat2: Latitude of point 2 in degrees.
    ///   - lon2: Longitude of point 2 in degrees.
    /// - Returns: Initial bearing in degrees (0° to 360°), where 0° is North, 90° is East, 180° is South, 270° is West.
    static func calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let lat1Rad = lat1 * .pi / 180.0
        let lon1Rad = lon1 * .pi / 180.0
        let lat2Rad = lat2 * .pi / 180.0
        let lon2Rad = lon2 * .pi / 180.0
        
        let dLon = lon2Rad - lon1Rad
        
        let y = sin(dLon) * cos(lat2Rad)
        let x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        
        var bearing = atan2(y, x) * 180.0 / .pi
        bearing = (bearing + 360.0).truncatingRemainder(dividingBy: 360.0)
        
        return bearing
    }
}
