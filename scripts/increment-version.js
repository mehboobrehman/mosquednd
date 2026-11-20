const fs = require('fs');
const path = require('path');

const packageJsonPath = path.join(__dirname, '..', 'package.json');
const versionPropsPath = path.join(__dirname, '..', 'app', 'version.properties');

console.log('Running automated version increment...');

// 1. Update package.json
if (fs.existsSync(packageJsonPath)) {
    const pkg = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
    const parts = pkg.version.split('.');
    parts[parts.length - 1] = parseInt(parts[parts.length - 1], 10) + 1;
    pkg.version = parts.join('.');
    fs.writeFileSync(packageJsonPath, JSON.stringify(pkg, null, 2) + '\n', 'utf8');
    console.log(`Updated package.json version to ${pkg.version}`);
}

// 2. Update app/version.properties
if (fs.existsSync(versionPropsPath)) {
    let propsContent = fs.readFileSync(versionPropsPath, 'utf8');
    let versionName = '1.0.0';
    let versionCode = 1;

    const nameMatch = propsContent.match(/VERSION_NAME=(.+)/);
    if (nameMatch) {
        const parts = nameMatch[1].trim().split('.');
        parts[parts.length - 1] = parseInt(parts[parts.length - 1], 10) + 1;
        versionName = parts.join('.');
    }

    const codeMatch = propsContent.match(/VERSION_CODE=(\d+)/);
    if (codeMatch) {
        versionCode = parseInt(codeMatch[1].trim(), 10) + 1;
    }

    propsContent = propsContent.replace(/VERSION_NAME=.+/, `VERSION_NAME=${versionName}`);
    propsContent = propsContent.replace(/VERSION_CODE=\d+/, `VERSION_CODE=${versionCode}`);

    fs.writeFileSync(versionPropsPath, propsContent, 'utf8');
    console.log(`Updated app/version.properties to VERSION_NAME=${versionName}, VERSION_CODE=${versionCode}`);
}

console.log('Version increment completed successfully.');
