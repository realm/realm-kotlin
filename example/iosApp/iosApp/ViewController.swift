import UIKit
import app

class ViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        label.text = Proxy().proxyHello()
        SampleKt.createRealmDB(name: "Sophia5", schema: "[ { \"name\": \"Person\", \"properties\": { \"name\": \"string\", \"age\": \"int\"}}]")
        
    }

    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
    }
    @IBOutlet weak var label: UILabel!
}
