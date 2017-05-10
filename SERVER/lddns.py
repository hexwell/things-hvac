import os
from flask import Flask, request, jsonify

app = Flask(__name__)
data = {'ip': None}


@app.route('/', methods=['POST', 'GET'])
def index():
	if request.method == 'GET':
		if data['ip']:
			return jsonify(data)
		else:
			return "Not registered", 204

	if request.method == 'POST':
		incoming = request.get_json()
		if isinstance(incoming, dict) and 'ip' in incoming:
			data['ip'] = incoming['ip']
			return jsonify({'success': True})

		return jsonify({'success': False})

app.run(host=os.getenv('IP', '0.0.0.0'), port=int(os.getenv('PORT', 8080)))
