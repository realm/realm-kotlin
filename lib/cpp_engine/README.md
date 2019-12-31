# Building

- Build the Shared Object library containing Realm with a C wrapper

```
# compile OS
cd realm-kotlin-mpp/lib/cpp_engine/external/realm-object-store
cmake .
make -j8
# compile the  wrapper library 
cd realm-kotlin-mpp/lib/cpp_engine/build
cmake .. .
make -j8
```
