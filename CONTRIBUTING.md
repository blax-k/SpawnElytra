# Contributing to SpawnElytra

Thank you for your interest in contributing to SpawnElytra! :)

## How to Contribute

### Reporting Bugs

If you find a bug, please create an issue using the bug report template. Include:

- A clear and descriptive title
- Steps to reproduce the issue
- Expected behavior vs actual behavior
- Your environment details (Minecraft version, server software, plugin version)
- Any relevant logs or screenshots

### Suggesting Features

Create an issue using the feature request template. Include:

- A clear description of the feature
- Why this feature would be useful
- Any implementation ideas you might have

### Code Contributions

1. **Fork the Repository**
   ```bash
   git clone https://github.com/blax-k/SpawnElytra.git
   cd SpawnElytra
   ```

2. **Create a Feature Branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```

3. **Make Your Changes**
   - Write clean, readable code
   - Follow the existing code style
   - Test your changes thoroughly
   - Update documentation if needed

4. **Build and Test**
   ```bash
   mvn clean package
   ```

5. **Commit Your Changes**
   ```bash
   git commit -m 'Add some amazing feature'
   ```
   - Use clear, descriptive commit messages
   - Reference issue numbers when applicable (e.g., "Fix #123")

6. **Push to Your Fork**
   ```bash
   git push origin feature/amazing-feature
   ```

7. **Open a Pull Request**
   - Provide a clear description of your changes
   - Link any related issues
   - Wait for review and address any feedback

## Development Guidelines

### Code Style

- Use meaningful variable and method names
- Add comments for complex logic
- Follow Java naming conventions
- Keep methods focused and concise

### Testing

- Test your changes on a local Minecraft server
- Verify compatibility with the supported Minecraft versions
- Check for any conflicts with common plugins

### Documentation

- Update the README.md if you add new features
- Update configuration examples if you add new config options
- Add comments to your code where necessary
