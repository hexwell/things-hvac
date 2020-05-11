import sqlite3
import json
import requests
import socket

from pyfcm import FCMNotification
from flask import Flask, render_template, jsonify, request, make_response

with open("data/admin.token") as token_file:
    admin_console_token = token_file.read()

with open("data/lddns") as url_file:
    url = url_file.read()

data = {'current': 0,
        'wanted': 0,}

fb_push_service = FCMNotification(api_key=admin_console_token)
app = Flask(__name__)
db = sqlite3.connect('data/tokens.db')
cursor = db.cursor()


@app.route('/', methods=['GET'])
def index():
    return render_template('index.html')


@app.route('/data/', methods=['POST', 'GET'])
def handle_data():
    if request.method == 'GET':
        out_data = {
            'current': data['current'],
            'wanted': data['wanted'],
        }
        return jsonify(out_data)

    if request.method == 'POST':
        in_data = request.get_json()

        valid = False
        while True:
            if not isinstance(in_data, dict):
                break
            if not ('wanted' in in_data or 'current' in in_data):
                break
            if 'wanted' in in_data:
                if not isinstance(data['wanted'], int):
                    break
                if not 0 <= data['wanted'] <= 40:
                    break
            if 'current' in in_data:
                if 'from_device' not in in_data:
                    break
                if not isinstance(data['current'], int):
                    break
                if not 0 <= data['current'] <= 40:
                    break
            valid = True
            break

        if not valid:
            return make_response('Invalid data', 400)
        else:
            if 'current' in in_data:
                data['current'] = in_data['current']
            if 'wanted' in in_data:
                data['wanted'] = in_data['wanted']

            if not 'from_device' in in_data:
                cursor.execute("SELECT * FROM tokens")
                ids = cursor.fetchall()
                if ids:
                    ids = [id_[0] for id_ in ids]
                    fcm_data = {'wanted': data['wanted']}
                    fb_push_service.\
                    notify_multiple_devices(registration_ids=ids,
                                            data_message=fcm_data)

            return jsonify({'success': True})


@app.route('/register/', methods=['POST'])
def register():
    in_data = request.get_json()
    if not in_data:
        return make_response('Please provide json data', 400)
    
    token = in_data.get('token')
    if not token:
        return make_response('Invalid data', 400)

    cursor.execute("SELECT * FROM tokens WHERE token = ?", (token,))
    if not cursor.fetchall():
        cursor.execute("INSERT INTO tokens (token) VALUES (?)", (token,))
        db.commit()

    clean_tokens()

    return jsonify({'success': True})


def clean_tokens():
    cursor.execute("SELECT * FROM tokens")
    ids = cursor.fetchall()
    if ids:
        ids = {id_[0] for id_ in ids}
        valid_ids = set(fb_push_service.clean_registration_ids(ids))
        for id_ in ids - valid_ids:
            cursor.execute("DELETE FROM tokens WHERE token = ?", (id_,))
        cursor.execute("SELECT * FROM tokens")
        db.commit()


def register_lddns():
    payload = {'ip': socket.gethostbyname(socket.gethostname())}
    print("Registering:", payload)
    headers = {'content-type': 'application/json'}
    return requests.post(url, data=json.dumps(payload),
                         headers=headers).status_code == requests.codes.ok


def main():
    clean_tokens()
    if not register_lddns():
        print("ERROR: LDDNS Registration failed!")
        return
    app.run(host='0.0.0.0', port='80')

if __name__ == "__main__":
    main()
