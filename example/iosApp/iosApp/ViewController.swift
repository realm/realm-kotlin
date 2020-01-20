import UIKit
import app

class ViewController: UIViewController {
    @IBOutlet weak var txtName: UITextField!
    @IBOutlet weak var txtAge: UITextField!
    @IBOutlet weak var labelAdults: UILabel!
    @IBOutlet weak var labelMinors: UILabel!
    
    @IBAction func btnAddPerson(_ sender: Any) {
        let name = txtName.text!
        let age = Int32(txtAge.text!)!
        app.PersonRepository().addPerson(name: name, age: age)
        updateCounts()
    }
    
    func updateCounts () {
        let aduluts = "\(app.PersonRepository().adultsCount()) 👴🏻👵"
        let minors = "\(app.PersonRepository().minorsCount()) 👦👶"
        
        labelAdults.text = aduluts
        labelMinors.text = minors
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        updateCounts()
    }

    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
    }
}
