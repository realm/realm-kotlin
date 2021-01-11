import SwiftUI
import shared

struct ContentView: View {
    let calculator = Calculator.Companion()
    let greet = Greeting().greeting()
    @State var token: LibraryRegistration? = nil
    
    init() {
        // FIXME onAppear/Disappear does not seem to be called like onResume/Pause,
        // so just register the callback in init for now
        listen()
    }
    
    func onAppear(perform action: (() -> Void)? = nil) -> some View {
        print("onAppear")
        listen()
        return self;
    }
    
    func onDisappear(perform action: (() -> Void)? = nil) -> some View {
        print("onDisappear")
        token?.cancel();
        return self;
    }
    
    private func listen() {
        do {
            self.token = try calculator.listen {
                // FIXME Update count through callback...but don't know how
                //  reference the class. Maybe encapsulate the listener in an
                //  observable object
                print("History updated")
            }
        } catch {
            print("error: \(error)")
        }
    }
    
    @State private var firstNum: String = "0"
    @State private var secondNum: String = "0"
    private var sum: String {
        if let firstNum = Int32(firstNum), let secondNum = Int32(secondNum) {
            return String(calculator.sum(a: firstNum, b: secondNum))
        } else {
            return "🤔"
        }
    }
    
    private var count: Int {
        return calculator.history().count
    }
    
    var body: some View {
        VStack(alignment: .center) {
            Text(greet)
            HStack(alignment: .center) {
                TextField("A", text: $firstNum)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.center)
                    .frame(width: 30)
                Text("+")
                TextField("B", text: $secondNum)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.center)
                    .frame(width: 30)
                Text("=")
                Text(sum)
            }
            Text("History count: " + String(count))
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
