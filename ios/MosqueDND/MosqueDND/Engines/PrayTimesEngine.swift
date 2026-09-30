import Foundation

struct PrayTimesEngine {

    static func calculate(
        date: Date,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone = .current,
        method: CalculationMethod = .mwl,
        asrMethod: AsrMethod = .standard,
        highLatitudeMethod: HighLatitudeMethod = .none,
        offsets: [PrayerType: Int] = [:]
    ) -> [PrayerType: PrayerTime] {
        var calendar = Calendar.current
        calendar.timeZone = timeZone
        
        let tzOffsetHours = Double(timeZone.secondsFromGMT(for: date)) / 3600.0
        
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        let year = components.year!
        let month = components.month!
        let day = components.day!
        
        let jd = julianDate(year: year, month: month, day: day)
        
        let D = jd - 2451545.0
        let dec = sunDeclination(D: D)
        let eqt = equationOfTime(D: D)
        
        let dhuhr = 12.0 + tzOffsetHours - longitude / 15.0 - eqt
        
        let sunriseHA = sunHourAngle(angle: -0.8333, lat: latitude, dec: dec)
        let sunrise = dhuhr - sunriseHA
        let sunset = dhuhr + sunriseHA
        
        var fajr = dhuhr - sunHourAngle(angle: -method.angles.fajrAngle, lat: latitude, dec: dec)
        
        var isha: Double
        if method.angles.ishaMinutes > 0 {
            isha = sunset + method.angles.ishaMinutes / 60.0
        } else {
            isha = dhuhr + sunHourAngle(angle: -method.angles.ishaAngle, lat: latitude, dec: dec)
        }
        
        let maghrib: Double = (method.angles.maghribAngle > 0) ?
            dhuhr + sunHourAngle(angle: -method.angles.maghribAngle, lat: latitude, dec: dec) : sunset
        
        let asrHA = asrHourAngle(lat: latitude, dec: dec, asrMethod: asrMethod)
        let asr = dhuhr + asrHA
        
        let nightHours = 24.0 - (sunset - sunrise)
        
        var adjustedFajr = fajr
        var adjustedIsha = isha
        
        if highLatitudeMethod != .none && abs(latitude) >= 48.0 {
            switch highLatitudeMethod {
            case .middleOfNight:
                let nightPortion = nightHours / 2.0
                adjustedFajr = sunrise - nightPortion
                adjustedIsha = sunset + nightPortion
            case .angleBased:
                let fajrPortion = nightHours * (method.angles.fajrAngle / 60.0)
                let ishaAngle = (method.angles.ishaMinutes > 0) ? method.angles.fajrAngle : method.angles.ishaAngle
                let ishaPortion = nightHours * (ishaAngle / 60.0)
                adjustedFajr = sunrise - fajrPortion
                adjustedIsha = sunset + ishaPortion
            case .nearestLatitude:
                let targetLat = (latitude > 0) ? 48.0 : -48.0
                adjustedFajr = dhuhr - sunHourAngle(angle: -method.angles.fajrAngle, lat: targetLat, dec: dec)
                if method.angles.ishaMinutes > 0 {
                    let targetSunset = dhuhr + sunHourAngle(angle: -0.8333, lat: targetLat, dec: dec)
                    adjustedIsha = targetSunset + method.angles.ishaMinutes / 60.0
                } else {
                    adjustedIsha = dhuhr + sunHourAngle(angle: -method.angles.ishaAngle, lat: targetLat, dec: dec)
                }
            default: break
            }
        }
        
        let rawTimes: [PrayerType: Double] = [
            .fajr: adjustedFajr,
            .sunrise: sunrise,
            .dhuhr: dhuhr,
            .asr: asr,
            .maghrib: maghrib,
            .isha: adjustedIsha
        ]
        
        var result: [PrayerType: PrayerTime] = [:]
        
        for (type, hours) in rawTimes {
            let offsetMin = Double(offsets[type] ?? 0)
            let adjustedHours = hours + offsetMin / 60.0
            
            let totalMinutes = Int(round(adjustedHours * 60.0))
            let normalized = ((totalMinutes % 1440) + 1440) % 1440
            let h = normalized / 60
            let m = normalized % 60
            
            var prayerCal = DateComponents()
            prayerCal.year = year
            prayerCal.month = month
            prayerCal.day = day
            prayerCal.hour = h
            prayerCal.minute = m
            prayerCal.second = 0
            prayerCal.timeZone = timeZone
            
            let time = calendar.date(from: prayerCal)!
            
            result[type] = PrayerTime(
                type: type,
                time: time,
                formattedTime: String(format: "%02d:%02d", h, m),
                isNext: false
            )
        }
        
        return result
    }
    
    private static func julianDate(year: Int, month: Int, day: Int) -> Double {
        var y = Double(year)
        var m = Double(month)
        if m <= 2 {
            y -= 1
            m += 12
        }
        let A = floor(y / 100.0)
        let B = 2 - A + floor(A / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + Double(day) + B - 1524.5
    }
    
    private static func sunDeclination(D: Double) -> Double {
        let g = fixAngle(357.529 + 0.98560028 * D)
        let q = fixAngle(280.459 + 0.98564736 * D)
        let L = fixAngle(q + 1.915 * sin(toRad(g)) + 0.020 * sin(toRad(2 * g)))
        let e = 23.439 - 0.00000036 * D
        return toDeg(asin(sin(toRad(e)) * sin(toRad(L))))
    }
    
    private static func equationOfTime(D: Double) -> Double {
        let g = fixAngle(357.529 + 0.98560028 * D)
        let q = fixAngle(280.459 + 0.98564736 * D)
        let L = fixAngle(q + 1.915 * sin(toRad(g)) + 0.020 * sin(toRad(2 * g)))
        let e = 23.439 - 0.00000036 * D
        let RA = fixHour(toDeg(atan2(cos(toRad(e)) * sin(toRad(L)), cos(toRad(L)))) / 15.0)
        return q / 15.0 - RA
    }
    
    private static func sunHourAngle(angle: Double, lat: Double, dec: Double) -> Double {
        let latRad = toRad(lat)
        let decRad = toRad(dec)
        let cosHA = (sin(toRad(angle)) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        if cosHA < -1.0 || cosHA > 1.0 { return 0.0 }
        return toDeg(acos(cosHA)) / 15.0
    }
    
    private static func asrHourAngle(lat: Double, dec: Double, asrMethod: AsrMethod) -> Double {
        let latRad = toRad(lat)
        let decRad = toRad(dec)
        let factor = asrMethod.shadowFactor
        let angle = atan(1.0 / (factor + tan(abs(latRad - decRad))))
        let cosHA = (sin(angle) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        if cosHA < -1.0 || cosHA > 1.0 { return 0.0 }
        return toDeg(acos(cosHA)) / 15.0
    }
    
    private static func toRad(_ deg: Double) -> Double { return deg * Double.pi / 180.0 }
    private static func toDeg(_ rad: Double) -> Double { return rad * 180.0 / Double.pi }
    private static func fixAngle(_ a: Double) -> Double { return a - 360.0 * floor(a / 360.0) }
    private static func fixHour(_ h: Double) -> Double { return h - 24.0 * floor(h / 24.0) }
}
