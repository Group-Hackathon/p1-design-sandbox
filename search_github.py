import urllib.request
import json
import time

url = "https://api.github.com/search/code?q=extension:obj+human+size:>10000"
headers = {"User-Agent": "Mozilla/5.0"}

req = urllib.request.Request(url, headers=headers)
try:
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
        for item in data.get('items', [])[:5]:
            print(item['name'], item['html_url'])
except Exception as e:
    print("Error:", e)
