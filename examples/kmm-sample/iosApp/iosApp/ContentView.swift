import SwiftUI
import shared

struct ContentView: View {
    let calculator = Calculator.Companion()
    let greet = Greeting().greeting()
    @State var token: LibraryCancellable? = nil
    var counter: Counter
    
    class Counter: ObservableObject {
        var count: Int
        init(count: Int) {
            self.count = count
        }
    }
    
    init () {
        counter = Counter(count : self.calculator.history().count)
    }
    
    private func listen()-> LibraryCancellable {
        do {
            return try calculator.listen {
                self.counter.count = self.calculator.history().count
                print("History updated \(self.counter.count)")
            }
        } catch {
            print("Failed to register for notifications: \(error)")
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
        return counter.count
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
        }.onAppear() {
            print("onAppear")
            self.token = self.listen()
        }.onDisappear() {
            print("onDisppear")
            self.token?.cancel()
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
