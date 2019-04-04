#coding: utf8
import http.server
import socketserver
import threading
import time
import socket
import random

HTTP_PORT = 8000

HTTP_HOST = "192.168.1.30"



class FakeYpIndexHandler(http.server.BaseHTTPRequestHandler):
  def do_GET(self):
    self.send_response(200)
    self.send_header("Content-Type", "text/plane; charset=utf8")
    
    if self.path.startswith("/index.txt"):
      s = FAKE_YP_INDEX 
      s = s.replace("%d"%random.randint(0, 9), "%d"%random.randint(0, 9))
      contentType = "text/plane; charset=utf8"
    elif self.path == "/yp4g.xml":
      s = fakeYpConfig()
      contentType = "text/xml; charset=utf8"
    else:
      s = "<a href=/index.txt>index.txt<br><a href=/yp4g.xml>yp4g.xml" 
      contentType = "text/html; charset=utf8"

    self.send_header("Content-Type", contentType)
    self.end_headers()
    self.wfile.write(s.encode('utf-8')) 

  def do_POST(self):
    self.send_response(200)
    self.send_header("Content-Type", "text/xml; charset=utf8")
    self.end_headers()

    kb = 200000
    while kb > 0:
      b = self.rfile.read(10000)
      print(len(b), "bytes..")
      kb -= len(b)
      time.sleep(0.1)
    self.wfile.write("ok".encode('utf-8')) 


class ThreadingTCPServer(socketserver.TCPServer, socketserver.ThreadingMixIn):
  pass

def main():
  with ThreadingTCPServer(("", HTTP_PORT), FakeYpIndexHandler) as server:
    #server.socket.setsockopt(
    #    socket.SOL_SOCKET,
    #    socket.SO_SNDBUF,
    #    1024)
    server.socket.setsockopt(
        socket.SOL_SOCKET,
        socket.SO_RCVBUF,
        1024*10)

    print("serving at port: ", HTTP_PORT)
    server.serve_forever()



FAKE_YP_INDEX = """よしかずch<>97573646A62D0C54FF842E144A5E732F<>218.110.22.64:7144<>http://jbbs.livedoor.jp/bbs/read.cgi/internet/2282/1257766959/<>game<>オフ会の反省会します - &lt;Open&gt;<>-1<>-1<>875<>WMV<><><><><>%E3%81%8B%E3%81%9A%E3%82%88%E3%81%97ch<>0:02<>click<>録画放送<>0
しろうとch<>97C4990E713FAE682500850EA3AEB4A1<>123.218.156.68:7144<>http://jbbs.livedoor.jp/netgame/7671/<>:PS3<>リハビリ期間中につき無言<>0<>0<>1463<>WMV<><><><><>%E3%81%AF%E3%81%93%E3%81%B3%E3%81%B3<>0:04<>click<>エンコテスト兼PCテスト<>0
放置だけch<>97C4990E713FAE682500850EA3AEB4A1<>123.218.156.68:7144<>http://jbbs.livedoor.jp/netgame/7671/<>:PS3<>ロボットOnline &lt;Open&gt;<>0<>0<>1463<>WMV<><><><><>%E3%81%AF%E3%81%93%E3%81%B3%E3%81%B3<>0:04<>click<>画質向上みこめないなぁ　プレマ<>0
いろはテレビ<>9712627A81F5FA2689B2E8FADE2C16E2<>122.133.172.10:7144<>http://yy701.60.kg/test/read.cgi/testt/1354422999/<>game<>不思議TA　短時間です - &lt;Free&gt;<>-1<>-1<>738<>WMV<><><><><>%E3%81%84%E3%82%8D%E3%81%AFch<>0:16<>click<><>0
もうどうにでもなあれch<>ABCD0008906F6F3EEB3CF753C54869FA<>123.45.67.89:7144<>http://yy62.60.kg/test/read.cgi/xxxyyyzzz/1343451246/<>Game<>360-P3U - &lt;Open&gt;<>-1<>-1<>724<>WMV<><><><><>%E3%81%A9%E3%81%86%E3%81%A7%E3%82%82%E3%81%84%E3%81%84%E3%82%88<>0:39<>click<><>0
デタラメさん<>97F32DEE944C88C6A55A0047F8735C31<>118.12.184.115:7144<>http://jbbs.livedoor.jp/bbs/read.cgi/game/47930/<>LoR<>League of Road - &lt;Free&gt;<>67<>75<>1209<>WMV<><><><><>%E3%81%8F%E3%81%A1%E3%81%B0%E3%81%97%E3%81%95%E3%82%93<>3:14<>click<><>0
山田の大冒険<>9D2F63C25ACF980812D346F3F73A8A89<>220.102.28.2:7164<>http://jbbs.livedoor.jp/radio/22156/<> game<>マリカ風ゲー - &lt;Free&gt;<>11<>12<>512<>WMV<><><><><>%E9%AB%98%E5%9D%82%E3%81%AE%E5%A4%A7%E5%86%92%E9%99%BA<>3:25<>click<>マイクの調子悪かったらレスでお願いします<>0
グァッシュ<>9705ED5CB685E4CF0AB16F53C1B27A88<>126.114.207.246:7144<>http://jbbs.livedoor.jp/bbs/read.cgi/game/41719/1360510719/<><>シリーズ屈指の神ゲー - &lt;Free&gt;<>791<>810<>758<>WMV<><><><><>%E3%83%B4%E3%82%A1%E3%83%83%E3%82%B7%E3%83%A5<>3:29<>click<>牛丼IP:vashtan.moe.hm:27888<>0
嘘Yellow Pages◆アップロード帯域<>00000000000000000000000000000000<><>http://temp.orz.hm/yp/uptest/<><>No data<>-9<>-9<>0<>RAW<><><>測定はコンタクトURLから<><><>00:00<>click<><>0
嘘YPからのお知らせ◆お知らせ<>00000000000000000000000000000000<><>http://temp.orz.hm/yp/rule.html<><>3/25 お知らせに注意情報を追加しました。必ず確認して下さい。<>-6<>-6<>0<>RAW<><><><><><>00:00<>click<><>0
"""

def fakeYpConfig():
  speed = random.randint(1, 1234)
  return """<?xml version="1.0" encoding="utf-8" ?>
<yp4g>
	<yp name="Fake Yellow Pages" />
	<host ip="123.456.0.0" port_open="1" speed="%d" over="1" />
	<uptest checkable="1" remain="0" />
	<uptest_srv addr="%s" port="%d" object="/uptest.cgi" post_size="250" limit="4500" interval="15" enabled="0" />
</yp4g>""" % (speed, HTTP_HOST, HTTP_PORT)


if __name__ == "__main__":
  main()

