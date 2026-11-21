const fs = require('fs');
const crypto = require('crypto');
const path = require('path');

const files = [
    'app/build/outputs/apk/debug/MosqueDND-debug-v1.0.19.apk',
    'app/build/outputs/apk/release/MosqueDND-release-v1.0.19.apk',
    'app/build/outputs/bundle/release/app-release.aab'
];

if (!fs.existsSync('release_assets')) {
    fs.mkdirSync('release_assets', { recursive: true });
}

let checksumOutput = '';
files.forEach(file => {
    if (fs.existsSync(file)) {
        const fileBuffer = fs.readFileSync(file);
        const hashSum = crypto.createHash('sha256');
        hashSum.update(fileBuffer);
        const hex = hashSum.digest('hex');
        const filename = path.basename(file);
        const line = `${hex}  ${filename}\n`;
        checksumOutput += line;
        
        const dest = path.join('release_assets', filename);
        fs.copyFileSync(file, dest);
        console.log(`Copied ${filename} to release_assets/ (${(fs.statSync(file).size / (1024*1024)).toFixed(2)} MB)`);
    } else {
        console.warn(`File not found: ${file}`);
    }
});

fs.writeFileSync('checksums.txt', checksumOutput, 'utf8');
fs.writeFileSync('release_assets/checksums.txt', checksumOutput, 'utf8');
console.log('Checksums generated successfully:\n' + checksumOutput);
