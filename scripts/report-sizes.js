const fs = require('fs');
const files = [
    'app/build/outputs/apk/debug/MosqueDND-debug-v1.0.19.apk',
    'app/build/outputs/apk/release/MosqueDND-release-v1.0.19.apk',
    'app/build/outputs/bundle/release/app-release.aab'
];
files.forEach(f => {
    if (fs.existsSync(f)) {
        const stats = fs.statSync(f);
        console.log(`${f} -> ${(stats.size / (1024*1024)).toFixed(2)} MB (${stats.size} bytes)`);
    }
});
