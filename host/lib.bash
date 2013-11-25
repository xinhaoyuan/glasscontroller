function require_command() {
    if command -v $1 >/dev/null 2>/dev/null; then 
        return 0
    else
        echo "require $1"
        return 1
    fi
}

function env2bash() {
    while read line; do
        if [[ $line =~ ^[^=]+= ]]; then
            if [ -n "$lastn" ]; then
                echo "export $lastn="$(printf '%q' "$lastv")
            fi
            lastn=`expr "$line" : "^\([^=]*\)="`
            lastv=`expr "$line" : "^[^=]*=\(.*\)$"`
        else
            lastv="$lastv"$'\n'"$line"
        fi
    done
    [ -n "$lastn" ] && echo "export $lastn="$(printf '%q' "$lastv")
}
