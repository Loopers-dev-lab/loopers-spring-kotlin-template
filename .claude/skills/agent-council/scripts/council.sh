#!/bin/bash
#
# Agent Council - Collect opinions from multiple AI Agents
#
# Usage: council.sh "question or prompt"
#        council.sh --stdin  (read from stdin)
#
# LLM Council (Karpathy) concept:
# - Stage 1: Send same question to each Agent
# - Stage 2: Collect and output responses
# - Stage 3: Claude synthesizes as Chairman (handled externally)
#

set -e

# Get script directory and find config
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_FILE="$SKILL_DIR/council.config.yaml"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Color name to code mapping
get_color_code() {
    case "$1" in
        RED) echo "$RED" ;;
        GREEN) echo "$GREEN" ;;
        BLUE) echo "$BLUE" ;;
        YELLOW) echo "$YELLOW" ;;
        CYAN) echo "$CYAN" ;;
        MAGENTA) echo "$MAGENTA" ;;
        *) echo "$NC" ;;
    esac
}

# Check arguments
if [ "$1" = "--stdin" ]; then
    PROMPT=$(cat)
elif [ -z "$1" ]; then
    echo -e "${RED}Error: Please provide a prompt${NC}"
    echo "Usage: $0 \"question or prompt\""
    echo "       $0 --stdin  (read from stdin)"
    exit 1
else
    PROMPT="$1"
fi

# Bug fix: removed duplicate PROMPT="$1" line

TEMP_DIR=$(mktemp -d)
PROMPT_FILE="$TEMP_DIR/prompt.txt"
echo "$PROMPT" > "$PROMPT_FILE"

# Parse YAML config (simple parser for our structure - macOS compatible)
parse_members() {
    if [ ! -f "$CONFIG_FILE" ]; then
        echo -e "${YELLOW}Warning: Config file not found at $CONFIG_FILE${NC}" >&2
        echo -e "${YELLOW}Using default configuration (codex, gemini)${NC}" >&2
        echo "codex|codex exec|🤖|BLUE"
        echo "gemini|gemini|💎|GREEN"
        return
    fi

    # Extract members using awk (macOS/BSD compatible)
    awk '
    /^  members:/ { in_members=1; next }
    /^  [a-z]/ && in_members { in_members=0 }
    in_members && /- name:/ {
        name=$3
        gsub(/"/, "", name)
    }
    in_members && /command:/ {
        cmd = $0
        sub(/.*command: *"?/, "", cmd)
        sub(/".*$/, "", cmd)
    }
    in_members && /emoji:/ {
        emoji = $2
        gsub(/"/, "", emoji)
    }
    in_members && /color:/ {
        color = $2
        gsub(/"/, "", color)
        if (name && cmd) {
            print name "|" cmd "|" emoji "|" color
            name=""; cmd=""; emoji=""; color=""
        }
    }
    ' "$CONFIG_FILE"
}

# Display truncated prompt (first 100 chars)
PROMPT_PREVIEW="${PROMPT:0:100}"
if [ ${#PROMPT} -gt 100 ]; then
    PROMPT_PREVIEW="${PROMPT_PREVIEW}..."
fi

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${CYAN}🏛️  Agent Council - Gathering Opinions${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${YELLOW}📝 Question:${NC} $PROMPT_PREVIEW"
echo ""

# Function to call an agent
call_agent() {
    local name="$1"
    local command="$2"
    local output_file="$TEMP_DIR/${name}.txt"
    local color_code="$3"

    echo -e "${color_code}[$name]${NC} Thinking..." >&2

    # Extract the base command (first word)
    local base_cmd=$(echo "$command" | awk '{print $1}')

    if command -v "$base_cmd" &> /dev/null; then
        # Handle different CLI tools
        case "$base_cmd" in
            codex)
                # codex exec requires prompt as argument, use temp file for long prompts
                codex exec "$(cat "$PROMPT_FILE")" > "$output_file" 2>&1 || echo "Error calling $name" > "$output_file"
                ;;
            gemini)
                # gemini can read from stdin with -p flag or pipe
                cat "$PROMPT_FILE" | gemini > "$output_file" 2>&1 || echo "Error calling $name" > "$output_file"
                ;;
            *)
                # Generic: try passing prompt as argument
                eval "$command \"\$(cat \"\$PROMPT_FILE\")\"" > "$output_file" 2>&1 || echo "Error calling $name" > "$output_file"
                ;;
        esac
    else
        echo "$name CLI not installed (command: $base_cmd)" > "$output_file"
    fi

    echo -e "${GREEN}[$name]${NC} Done" >&2
}

# Stage 1: Collect members and call in parallel
echo -e "${YELLOW}Stage 1: Collecting opinions from council members...${NC}"
echo ""

# Read members and start parallel calls
declare -a PIDS
declare -a MEMBERS

while IFS='|' read -r name cmd emoji color; do
    [ -z "$name" ] && continue
    MEMBERS+=("$name|$emoji|$color")
    color_code=$(get_color_code "$color")
    call_agent "$name" "$cmd" "$color_code" &
    PIDS+=($!)
done < <(parse_members)

# Wait for all agents
for pid in "${PIDS[@]}"; do
    wait "$pid" 2>/dev/null || true
done

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${CYAN}Stage 2: Council Opinions${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Display each member's response
for member_info in "${MEMBERS[@]}"; do
    IFS='|' read -r name emoji color <<< "$member_info"
    color_code=$(get_color_code "$color")
    output_file="$TEMP_DIR/${name}.txt"

    echo -e "${color_code}┌─────────────────────────────────────────────────────────┐${NC}"
    echo -e "${color_code}│ ${emoji} ${name}${NC}"
    echo -e "${color_code}└─────────────────────────────────────────────────────────┘${NC}"

    if [ -f "$output_file" ]; then
        cat "$output_file"
    else
        echo "No response"
    fi
    echo ""
done

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${CYAN}Stage 3: Claude (Chairman) will synthesize above opinions${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Cleanup
rm -rf "$TEMP_DIR"