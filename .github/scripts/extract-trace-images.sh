#!/bin/bash

set -uo pipefail

# Script to extract PNG and JPEG images from Playwright trace files
# Usage: extract-trace-images.sh <input_dir> <output_dir>
#
# This script:
# 1. Finds all .zip trace files in the input directory
# 2. Extracts all PNG and JPEG files from the resources/ folder
# 3. Filters out blank pages (files less than 5 KB)
# 4. Consolidates all images into a single output directory

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <input_dir> <output_dir>"
    echo "Example: $0 build/playwright-traces extracted-screenshots"
    exit 1
fi

INPUT_DIR="$1"
OUTPUT_DIR="$2"

# Check if input directory exists
if [ ! -d "$INPUT_DIR" ]; then
    echo "Error: Input directory '$INPUT_DIR' does not exist"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Create a temporary working directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

echo "Extracting images from trace files in $INPUT_DIR..."

# Counter for statistics
total_traces=0
total_images=0
filtered_images=0

# Find all .zip files in the input directory
while IFS= read -r -d '' trace_file; do
    ((total_traces++))
    trace_name=$(basename "$trace_file" .zip)
    echo "Processing: $trace_name"
    
    # Create a temporary directory for this trace
    trace_temp="$TEMP_DIR/$trace_name"
    mkdir -p "$trace_temp"
    
    # Extract the trace file
    unzip -q "$trace_file" -d "$trace_temp" 2>/dev/null || {
        echo "  Warning: Failed to extract $trace_file"
        continue
    }
    
    # Check if resources directory exists
    if [ ! -d "$trace_temp/resources" ]; then
        echo "  No resources directory found"
        continue
    fi
    
    # Find all PNG and JPEG files in resources
    image_count=0
    while IFS= read -r -d '' image_file; do
        ((total_images++))
        
        # Get file size in KB
        file_size=$(stat -f%z "$image_file" 2>/dev/null || stat -c%s "$image_file" 2>/dev/null)
        file_size_kb=$((file_size / 1024))
        
        # Filter out blank pages (less than 5 KB)
        if [ "$file_size_kb" -lt 5 ]; then
            echo "  Skipping small image: $(basename "$image_file") (${file_size_kb}KB)"
            continue
        fi
        
        ((filtered_images++))
        ((image_count++))
        
        # Create a unique filename with trace name prefix
        image_basename=$(basename "$image_file")
        output_name="${trace_name}_${image_basename}"
        
        # Copy to output directory
        cp "$image_file" "$OUTPUT_DIR/$output_name"
    done < <(find "$trace_temp/resources" -type f \( -iname "*.png" -o -iname "*.jpg" -o -iname "*.jpeg" \) -print0)
    
    echo "  Extracted $image_count images"
done < <(find "$INPUT_DIR" -maxdepth 1 -type f -name "*.zip" -print0)

echo ""
echo "Summary:"
echo "  Traces processed: $total_traces"
echo "  Total images found: $total_images"
echo "  Images extracted (after filtering): $filtered_images"
echo "  Output directory: $OUTPUT_DIR"

if [ "$filtered_images" -eq 0 ]; then
    echo ""
    echo "Warning: No images were extracted. This might indicate:"
    echo "  - No trace files found in $INPUT_DIR"
    echo "  - All images were smaller than 5KB (blank pages)"
    echo "  - Trace files don't contain screenshot data"
fi
