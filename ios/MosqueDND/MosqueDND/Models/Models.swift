import Foundation

// MARK: - Mosque

struct Mosque: Codable, Identifiable {
    var id: String = UUID().uuidString
    var name: String = ""
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var address: String = ""
    var geofenceRadius: Float = 50.0
}

// MARK: - Prayer Enums

enum PrayerType: String, Codable, CaseIterable {
    case fajr = "fajr"
    case sunrise = "sunrise"
    case dhuhr = "dhuhr"
    case asr = "asr"
    case maghrib = "maghrib"
    case isha = "isha"
}

struct PrayerTime {
    let type: PrayerType
    let time: Date
    let formattedTime: String
    let isNext: Bool
}

enum CalculationMethod: String, Codable, CaseIterable {
    case mwl = "mwl"
    case isna = "isna"
    case egypt = "egypt"
    case ummAlQura = "umm_al_qura"
    case karachi = "karachi"
    case tehran = "tehran"
    case jafari = "jafari"
}

struct MethodAngles {
    let fajrAngle: Double
    let ishaAngle: Double
    let ishaMinutes: Double
    let maghribAngle: Double
}

extension CalculationMethod {
    var angles: MethodAngles {
        switch self {
        case .mwl:
            return MethodAngles(fajrAngle: 18.0, ishaAngle: 17.0, ishaMinutes: 0.0, maghribAngle: 0.0)
        case .isna:
            return MethodAngles(fajrAngle: 15.0, ishaAngle: 15.0, ishaMinutes: 0.0, maghribAngle: 0.0)
        case .egypt:
            return MethodAngles(fajrAngle: 19.5, ishaAngle: 17.5, ishaMinutes: 0.0, maghribAngle: 0.0)
        case .ummAlQura:
            return MethodAngles(fajrAngle: 18.5, ishaAngle: 0.0, ishaMinutes: 90.0, maghribAngle: 0.0)
        case .karachi:
            return MethodAngles(fajrAngle: 18.0, ishaAngle: 18.0, ishaMinutes: 0.0, maghribAngle: 0.0)
        case .tehran:
            return MethodAngles(fajrAngle: 17.7, ishaAngle: 14.0, ishaMinutes: 0.0, maghribAngle: 4.5)
        case .jafari:
            return MethodAngles(fajrAngle: 16.0, ishaAngle: 14.0, ishaMinutes: 0.0, maghribAngle: 4.0)
        }
    }
}
enum AsrMethod: String, Codable, CaseIterable {
    case standard = "standard"
    case hanafi = "hanafi"
}

extension AsrMethod {
    var shadowFactor: Double {
        switch self {
        case .standard: return 1.0
        case .hanafi: return 2.0
        }
    }
}


enum HighLatitudeMethod: String, Codable, CaseIterable {
    case none = "none"
    case nearestLatitude = "nearest_latitude"
    case angleBased = "angle_based"
    case middleOfNight = "middle_of_night"
}

// MARK: - ExportData

struct ExportData: Codable {
    var exportVersion: Int = 1
    var exportedAt: String = ""
    
    var calculationMethod: String = CalculationMethod.mwl.rawValue
    var asrMethod: String = AsrMethod.standard.rawValue
    var highLatitudeMethod: String = HighLatitudeMethod.none.rawValue
    
    var masterAlarmEnabled: Bool = true
    
    var prayerOffsets: [String: Int]? = nil
    var prayerAlarms: [String: Bool]? = nil
    
    var customAdhanUri: String? = nil
    
    var savedMosques: [Mosque] = []
    
    var themePreference: String = "system"
    
    var dndEnabled: [String: Bool]? = nil
    var dndOffsets: [String: Int]? = nil
    var dndDurations: [String: Int]? = nil
    
}
