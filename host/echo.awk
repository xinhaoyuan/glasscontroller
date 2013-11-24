BEGIN {
    current = 0
}

/next/ {
    current += 1
}

/prev/ {
    current -= 1
}

/skip/ {
    current += $3
}

{
    if ($1 ~ /^-?[0-9]+/) print $1 "&" current
}
